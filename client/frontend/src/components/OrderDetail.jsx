import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  Grid,
  Paper,
  Typography,
} from '@mui/material';
import { OrderService } from '../services/OrderService';

const formatDateTime = (timestamp) => {
  if (!timestamp) return 'N/A';
  return new Date(timestamp).toLocaleString();
};

const getStatusColor = (status) => {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'REJECTED':
    case 'EXPIRED':
      return 'error';
    case 'IN_PROGRESS':
      return 'primary';
    default:
      return 'default';
  }
};

const OrderDetail = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  
  const [orderStatus, setOrderStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  
  const [confirmDialog, setConfirmDialog] = useState({
    open: false,
    title: '',
    message: '',
    action: null
  });
  
  const closeConfirmDialog = () => {
    setConfirmDialog({ ...confirmDialog, open: false });
  };
  
  const fetchOrderStatus = async () => {
    try {
      setLoading(true);
      const data = await OrderService.getOrderStatus(orderId);
      setOrderStatus(data);
      setError(null);
    } catch (err) {
      console.error('Error fetching order status:', err);
      setError('Failed to load order details. Please try again.');
    } finally {
      setLoading(false);
    }
  };
  
  useEffect(() => {
    fetchOrderStatus();
    
    // Set up polling for updates
    const intervalId = setInterval(fetchOrderStatus, 5000); // Poll every 5 seconds
    
    return () => clearInterval(intervalId); // Clean up on unmount
  }, [orderId, refreshKey]);
  
  const handleRefresh = () => {
    setRefreshKey(prevKey => prevKey + 1);
  };
  
  const handleAcceptQuote = async () => {
    setConfirmDialog({
      open: true,
      title: 'Confirm Quote Acceptance',
      message: `Are you sure you want to accept the quote of $${orderStatus?.quote?.price.toFixed(2)}?`,
      action: async () => {
        try {
          await OrderService.acceptQuote(orderId);
          closeConfirmDialog();
          handleRefresh();
        } catch (error) {
          console.error('Error accepting quote:', error);
          setError('Failed to accept quote. Please try again.');
          closeConfirmDialog();
        }
      }
    });
  };
  
  const handleRejectQuote = async () => {
    setConfirmDialog({
      open: true,
      title: 'Confirm Quote Rejection',
      message: 'Are you sure you want to reject this quote?',
      action: async () => {
        try {
          await OrderService.rejectQuote(orderId);
          closeConfirmDialog();
          handleRefresh();
        } catch (error) {
          console.error('Error rejecting quote:', error);
          setError('Failed to reject quote. Please try again.');
          closeConfirmDialog();
        }
      }
    });
  };

  if (loading && !orderStatus) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {error}
        <Button color="inherit" size="small" onClick={handleRefresh} sx={{ ml: 2 }}>
          Retry
        </Button>
      </Alert>
    );
  }

  if (!orderStatus) {
    return (
      <Alert severity="warning">
        Order not found. The ID may be incorrect or the order has been deleted.
        <Button color="inherit" size="small" onClick={() => navigate('/orders')} sx={{ ml: 2 }}>
          Back to Orders
        </Button>
      </Alert>
    );
  }

  const isQuoteActive = orderStatus.status === 'IN_PROGRESS' && orderStatus.quote && !orderStatus.quote.isExpired;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Order Details</Typography>
        <Button variant="outlined" onClick={handleRefresh} disabled={loading}>
          Refresh
        </Button>
      </Box>
      
      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Paper sx={{ p: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="h6">Order Information</Typography>
              <Chip
                label={orderStatus.status}
                color={getStatusColor(orderStatus.status)}
              />
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Typography variant="subtitle2">Order ID</Typography>
                <Typography variant="body1" sx={{ mb: 1 }}>{orderStatus.orderId}</Typography>
                
                <Typography variant="subtitle2">Workflow ID</Typography>
                <Typography variant="body1" sx={{ mb: 1 }}>{orderStatus.workflowId}</Typography>
              </Grid>
            </Grid>
          </Paper>
        </Grid>
        
        {orderStatus.quote && (
          <Grid item xs={12}>
            <Card>
              <CardHeader title="Quote Details" />
              <CardContent>
                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <Typography variant="subtitle2">Price</Typography>
                    <Typography variant="h4" color="primary">${orderStatus.quote.price.toFixed(2)}</Typography>
                  </Grid>
                  
                  <Grid item xs={12} md={6}>
                    <Typography variant="subtitle2">Expires At</Typography>
                    <Typography variant="body1">
                      {formatDateTime(orderStatus.quote.expiresAt)}
                    </Typography>
                    
                    {orderStatus.quote.isExpired ? (
                      <Chip 
                        label="Expired" 
                        color="error" 
                        size="small" 
                        sx={{ mt: 1 }} 
                      />
                    ) : (
                      <Chip 
                        label="Active" 
                        color="success" 
                        size="small"
                        sx={{ mt: 1 }} 
                      />
                    )}
                  </Grid>
                  
                  {isQuoteActive && (
                    <Grid item xs={12} sx={{ mt: 2 }}>
                      <Box sx={{ display: 'flex', gap: 2 }}>
                        <Button 
                          variant="contained" 
                          color="primary"
                          onClick={handleAcceptQuote}
                        >
                          Accept Quote
                        </Button>
                        <Button 
                          variant="outlined" 
                          color="error"
                          onClick={handleRejectQuote}
                        >
                          Reject Quote
                        </Button>
                      </Box>
                    </Grid>
                  )}
                </Grid>
              </CardContent>
            </Card>
          </Grid>
        )}
      </Grid>
      
      {/* Confirmation Dialog */}
      <Dialog open={confirmDialog.open} onClose={closeConfirmDialog}>
        <DialogTitle>{confirmDialog.title}</DialogTitle>
        <DialogContent>
          <DialogContentText>{confirmDialog.message}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeConfirmDialog}>Cancel</Button>
          <Button onClick={confirmDialog.action} autoFocus>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default OrderDetail;

import { useState, useEffect } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Grid,
  Typography,
  Paper,
} from '@mui/material';
import { OrderService } from '../services/OrderService';

const Dashboard = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [stats, setStats] = useState({
    total: 0,
    inProgress: 0,
    completed: 0,
    rejected: 0
  });
  
  const fetchOrders = async () => {
    try {
      setLoading(true);
      const data = await OrderService.getAllOrders();
      setOrders(data);
      
      // Calculate stats
      const statsData = {
        total: data.length,
        inProgress: data.filter(order => order.status === 'IN_PROGRESS').length,
        completed: data.filter(order => order.status === 'COMPLETED').length,
        rejected: data.filter(order => 
          order.status === 'REJECTED' || order.status === 'EXPIRED'
        ).length
      };
      
      setStats(statsData);
      setError(null);
    } catch (err) {
      console.error('Error fetching orders:', err);
      setError('Failed to load dashboard data. Please try again.');
    } finally {
      setLoading(false);
    }
  };
  
  useEffect(() => {
    fetchOrders();
    
    // Set up polling for updates
    const intervalId = setInterval(fetchOrders, 10000); // Poll every 10 seconds
    
    return () => clearInterval(intervalId); // Clean up on unmount
  }, []);

  // Get orders with active quotes that need attention
  const ordersNeedingAction = orders.filter(
    order => order.status === 'IN_PROGRESS' && order.quote && !order.quote.isExpired
  );

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>
      
      {loading && !orders.length ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
          <CircularProgress />
        </Box>
      ) : error ? (
        <Typography color="error">{error}</Typography>
      ) : (
        <Grid container spacing={3}>
          {/* Stats cards */}
          <Grid item xs={12} md={3}>
            <Paper sx={{ p: 2, textAlign: 'center', height: '100%' }}>
              <Typography variant="h3" color="primary">{stats.total}</Typography>
              <Typography variant="subtitle1">Total Orders</Typography>
            </Paper>
          </Grid>
          
          <Grid item xs={12} md={3}>
            <Paper sx={{ p: 2, textAlign: 'center', height: '100%', bgcolor: 'info.light', color: 'info.contrastText' }}>
              <Typography variant="h3">{stats.inProgress}</Typography>
              <Typography variant="subtitle1">In Progress</Typography>
            </Paper>
          </Grid>
          
          <Grid item xs={12} md={3}>
            <Paper sx={{ p: 2, textAlign: 'center', height: '100%', bgcolor: 'success.light', color: 'success.contrastText' }}>
              <Typography variant="h3">{stats.completed}</Typography>
              <Typography variant="subtitle1">Completed</Typography>
            </Paper>
          </Grid>
          
          <Grid item xs={12} md={3}>
            <Paper sx={{ p: 2, textAlign: 'center', height: '100%', bgcolor: 'error.light', color: 'error.contrastText' }}>
              <Typography variant="h3">{stats.rejected}</Typography>
              <Typography variant="subtitle1">Rejected/Expired</Typography>
            </Paper>
          </Grid>
          
          {/* Orders needing action */}
          <Grid item xs={12}>
            <Card>
              <CardHeader 
                title="Quotes Requiring Action" 
                action={
                  <Button component={RouterLink} to="/orders/new" variant="contained">
                    New Order
                  </Button>
                }
              />
              <CardContent>
                {ordersNeedingAction.length === 0 ? (
                  <Typography>
                    No quotes currently need your attention.
                  </Typography>
                ) : (
                  <Grid container spacing={2}>
                    {ordersNeedingAction.slice(0, 3).map(order => (
                      <Grid item xs={12} md={4} key={order.orderId}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="h6" sx={{ mb: 1 }}>
                              Order {order.orderId.substring(0, 8)}...
                            </Typography>
                            
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                              Quote: ${order.quote?.price.toFixed(2) || 'N/A'}
                            </Typography>
                            
                            <Button 
                              component={RouterLink}
                              to={`/orders/${order.orderId}`}
                              variant="outlined"
                              size="small"
                              sx={{ mt: 1 }}
                            >
                              View Details
                            </Button>
                          </CardContent>
                        </Card>
                      </Grid>
                    ))}
                  </Grid>
                )}
                
                {ordersNeedingAction.length > 3 && (
                  <Box sx={{ mt: 2, textAlign: 'center' }}>
                    <Button 
                      component={RouterLink}
                      to="/orders"
                      variant="text"
                    >
                      View All Orders
                    </Button>
                  </Box>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}
    </Box>
  );
};

export default Dashboard;
